package com.template;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.contracts.Command;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

import java.security.PublicKey;
import java.util.Arrays;
import java.util.List;

// ******************
// * Initiator flow *
// ******************

/**
 * Initiator flow
 *  - @InitiatingFlow means this is part of a flow pair - it triggers the other side to run the
 *    counterpart flow.
 *  - @StartableByRPC allows the node owner to start this flow via an RPC call
 */
@InitiatingFlow
@StartableByRPC
public class IOUFlow extends FlowLogic<Void> {
    private final Integer iouValue;

    // Refers to the borrower in this case since only lenders initiate the flow.
    private final Party otherParty;

    /**
     * The progress tracker provides checkpoints indicating the progress of the flow to observers.
     */
    private final ProgressTracker progressTracker = new ProgressTracker();

    public IOUFlow(Integer iouValue, Party otherParty) {
        this.iouValue = iouValue;
        this.otherParty = otherParty;
    }

    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    /**
     * The flow logic is encapsulated within the call() method.
     *  - Return type must match type parameter passed to FlowLogic (in this case: Void).
     *  - @Suspendable allows the flow to be check-pointed and serialized to disk when it
     *    encounters a long-running operation. This allows a node to move on to running
     *    other flows.
     */
    @Suspendable
    @Override
    public Void call() throws FlowException {
        // We retrieve the notary identity from the network map.
        Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

        // We create the transaction components.
        IOUState outputState = new IOUState(iouValue, getOurIdentity(), otherParty);
        // Adding both the lender and borrowers public key means that for the transaction to be valid,
        // they are both required to sign the transaction.
        List<PublicKey> requiredSigners = Arrays.asList(getOurIdentity().getOwningKey(), otherParty.getOwningKey());
        Command command = new Command<>(new IOUContract.Create(), requiredSigners);

        // We create a transaction builder and add the components.
        // Notice there are no inputs to the transaction: this is because we are not consuming
        // any existing ledger states in the creation of the IOU.
        // Output state requires both the output state itself and a reference to the contract
        // that governs the evolution of state over time.
        TransactionBuilder txBuilder = new TransactionBuilder(notary)
                .addOutputState(outputState, IOUContract.ID)
                .addCommand(command);

        // Verifying the transaction.
        // This tests to make sure that our transaction proposal satisfies the requirements
        // of our contract.
        txBuilder.verify(getServiceHub());

        // Signing the transaction.
        // Once a transaction is signed, no one will be able to modify it without invalidating
        // the signature.
        SignedTransaction signedTx = getServiceHub().signInitialTransaction(txBuilder);

        // Creating a session with the other party (borrower).
        FlowSession otherPartySession = initiateFlow(otherParty);

        SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(
                signedTx, Arrays.asList(otherPartySession), CollectSignaturesFlow.tracker()));

        // Finalising the transaction.
        subFlow(new FinalityFlow(signedTx));

        return null;
    }
}
